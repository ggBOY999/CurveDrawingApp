/**
 * CurveDrawingApp 是一个基于 JavaFX 的高级曲线绘制应用程序
 * 支持贝塞尔曲线绘制、控制点操作和视图变换功能
 * 该应用允许用户通过添加控制点来创建平滑的曲线，并提供了对控制点和控制句柄的交互式编辑能力
 */
package com.example.curvedrawing;

//头文件定义
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * 高级曲线绘制应用程序，支持贝塞尔曲线、控制点操作和视图变换
 */
public class CurveDrawingApp extends Application {

    // 画布尺寸常量
    private static final double CANVAS_WIDTH = 800;
    private static final double CANVAS_HEIGHT = 600;
    private static final double CONTROL_POINT_RADIUS = 3; // 控制点显示半径

    // 图形相关组件
    private Canvas canvas; // 画布
    private GraphicsContext gc; // 图形上下文

    // 控制点数据
    private final List<Point> controlPoints = new ArrayList<>(); // 存储所有控制点
    private Point currentMousePosition; // 当前鼠标位置（用于预览）
    private boolean isDrawing = false; // 表示是否在绘制
    private boolean finished = false; // 表示绘制是否完成

    // 选择相关状态
    private int selectedPointIndex = -1; // 当前选中的控制点索引
    private HandleType selectedHandleType = HandleType.POINT; // 选择的句柄类型

    // 视图变换参数
    private double scale = 1.0; // 缩放比例
    private final Point translate = new Point(0, 0); // 平移偏移量
    private Point lastDragPosition; // 上次拖拽位置（用于平移计算）

    /**
     * 控制点句柄类型枚举：
     * POINT - 控制点本身
     * OUT_HANDLE - 输出方向的控制句柄
     * IN_HANDLE - 输入方向的控制句柄
     */
    private enum HandleType {
        POINT, //表示选中控制点
        OUT_HANDLE, //表示选中输出句柄
        IN_HANDLE //表示选中输入句柄
    }

    /**
     * 程序入口
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
      JavaFX 应用程序的主入口方法，初始化UI组件和事件处理器
     */
    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane(); //实例化一个 BorderPane 布局容器
        initializeCanvas(); //对画布进行初始化设置
        setupEventHandlers(); //设置事件处理器
        root.setCenter(canvas); //将画布放置在 BorderPane 的中心区域
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT); //实例化 Scene 对象
        primaryStage.setTitle("Advanced Curve Drawer"); //设置标题
        primaryStage.setScene(scene); //将创建好的 scene 关联到 primaryStage
        primaryStage.show(); //显示窗口
        initializeDrawingContext();//初始化绘图上下文
    }

    /**
     * 初始化绘图上下文参数
     */
    private void initializeDrawingContext() {
        gc.setLineWidth(1); //设置线宽
        clearCanvas(); //清空画布
    }

    /**
     * 初始化画布设置
     */
    private void initializeCanvas() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT); //设置画布宽高
        gc = canvas.getGraphicsContext2D(); //获取图形上下文
    }

    /**
     * 设置各种事件处理器
     */
    private void setupEventHandlers() {
        canvas.setOnMouseMoved(this::handleMouseMove); //鼠标移动
        canvas.setOnMousePressed(this::handleMousePressed); //鼠标按下
        canvas.setOnMouseDragged(this::handleMouseDragged); //鼠标拖拽
        canvas.setOnMouseReleased(e -> { //鼠标抬起
            selectedPointIndex = -1; //当前未选择任何控制点，索引设置为-1
            selectedHandleType = HandleType.POINT; //选择的控制点句柄类型设置为控制点本身
        });
        canvas.setOnMouseClicked(this::handleMouseClicked); //鼠标点击
        canvas.setOnScroll(this::handleScroll); //滚轮滚动
    }

    /**
     * 处理滚轮缩放事件
     */
    private void handleScroll(ScrollEvent e) {
        double zoomFactor = 1.1; // 缩放系数
        double oldScale = scale; //原本的比例
        Point mousePos = new Point(e.getX(), e.getY()); //获取鼠标位置

        // 计算新的缩放比例（限制在0.1-10倍之间）
        if (e.getDeltaY() > 0) {
            scale *= zoomFactor; //放大
        } else {
            scale /= zoomFactor; //缩小
        }
        scale = Math.min(Math.max(0.1, scale), 20); //限制放大的比例

        // 计算缩放后的平移偏移量，保持鼠标位置不变
        translate.x = mousePos.x - (mousePos.x - translate.x) * (scale / oldScale);
        translate.y = mousePos.y - (mousePos.y - translate.y) * (scale / oldScale);

        redrawCanvas(); //重绘整个画布
    }

    /**
     * 处理鼠标按下事件（选择控制点或开始平移）
     */
    private void handleMousePressed(MouseEvent e) {
        lastDragPosition = new Point(e.getX(), e.getY()); //记录按下位置

        // 优先检查是否选中了输出句柄
        for (int i = 0; i < controlPoints.size(); i++) {
            Point p = controlPoints.get(i);
            if (p.outHandle != null && p.showHandle) { //该点有输出句柄并且显示了句柄
                Point handleScreen = canvasToScreen(p.outHandle); //将句柄坐标转换为屏幕坐标
                //计算鼠标位置和句柄坐标的距离，满足条件表示选中输出句柄
                if (distance(handleScreen, new Point(e.getX(), e.getY())) < CONTROL_POINT_RADIUS * 2) {
                    selectedPointIndex = i; //修改选中的点
                    selectedHandleType = HandleType.OUT_HANDLE; //修改句柄类型
                    return;
                }
            }

            // 检查输入句柄
            if (p.inHandle != null && p.showHandle) { //该点有输入句柄并且显示了句柄
                Point handleScreen = canvasToScreen(p.inHandle); //将句柄坐标转换为屏幕坐标
                //计算鼠标位置和句柄坐标的距离，满足条件表示选中输入句柄
                if (distance(handleScreen, new Point(e.getX(), e.getY())) < CONTROL_POINT_RADIUS * 2) {
                    selectedPointIndex = i; //修改选中的点
                    selectedHandleType = HandleType.IN_HANDLE; //修改句柄类型
                    return;
                }
            }
        }

        // 检查控制点本身
        for (int i = 0; i < controlPoints.size(); i++) {
            Point p = controlPoints.get(i);
            Point pScreen = canvasToScreen(p); //将句柄坐标转换为屏幕坐标
            //计算鼠标位置和控制点的距离，满足条件表示选中控制点
            if (distance(pScreen, new Point(e.getX(), e.getY())) < CONTROL_POINT_RADIUS * 2) {
                if(finished) {
                    p.showHandle = !p.showHandle; //点击控制点时显示句柄,再次点击关闭句柄
                }
                selectedPointIndex = i; //修改选中的点
                selectedHandleType = HandleType.POINT; //修改句柄类型
                redrawCanvas(); // 重回画布，显示句柄
                return;
            }
        }

        // 右键按下标记为平移模式
        if (e.isSecondaryButtonDown()) {
            selectedPointIndex = -2; //用-2标记我们要进行平移操作
        }
    }

    /**
     * 处理鼠标拖拽事件（移动控制点或平移视图）
     */
    private void handleMouseDragged(MouseEvent e) {
        if (selectedPointIndex >= 0) {  // 控制点或者句柄拖拽
            Point canvasPos = screenToCanvas(new Point(e.getX(), e.getY()));
            Point p = controlPoints.get(selectedPointIndex); //获取当前调整的点

            switch (selectedHandleType) {
                case POINT:  // 移动整个控制点及其关联句柄
                    double deltaX = canvasPos.x - p.x;
                    double deltaY = canvasPos.y - p.y;
                    p.x = canvasPos.x;
                    p.y = canvasPos.y;

                    // 同步移动关联句柄
                    if (p.outHandle != null) {
                        p.outHandle.x += deltaX;
                        p.outHandle.y += deltaY;
                    }
                    if (p.inHandle != null) {
                        p.inHandle.x += deltaX;
                        p.inHandle.y += deltaY;
                    }
                    break;
                case OUT_HANDLE:  //移动输出句柄
                    p.outHandle = canvasPos; //输出句柄的位置为鼠标的位置
                    p.inHandle = new Point(2*p.x - canvasPos.x, 2*p.y - canvasPos.y);//输入句柄和输出句柄关于控制点对称
                    break;
                case IN_HANDLE:   // 移动输入句柄
                    p.inHandle = canvasPos; //输入句柄的位置为鼠标的位置
                    p.outHandle = new Point(2*p.x - canvasPos.x, 2*p.y - canvasPos.y);//输出句柄和输入句柄关于控制点对称
                    break;
            }
            redrawCanvas();
        } else if (selectedPointIndex == -2) {  // 视图平移模式
            translate.x += e.getX() - lastDragPosition.x;
            translate.y += e.getY() - lastDragPosition.y;
            lastDragPosition = new Point(e.getX(), e.getY()); //移动后的位置更新为上次位置
            redrawCanvas();
        }
    }

    /**
     * 处理鼠标点击事件（左键添加点，右键重置，双击完成曲线）
     */
    private void handleMouseClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY) { //鼠标左键点击
            if (e.getClickCount() == 2) {
                handleDoubleClick(); //处理左键双击事件
            } else {
                handleLeftClick(e); //处理左键单击事件
            }
        } else if (e.getButton() == MouseButton.SECONDARY) { //鼠标右键点击
            if (e.getClickCount() == 2) { //右键双击清空画布以及所有点
                controlPoints.clear();
                isDrawing = false; //绘画状态置为false
                finished = false; //完成状态置为false
                currentMousePosition = null;
                redrawCanvas();
            }
        }
    }

    /**
     * 处理双击事件 - 完成曲线绘制并计算贝塞尔控制句柄
     */
    private void handleDoubleClick() {
        if (controlPoints.size() >= 2 && isDrawing) { //处于绘画状态时进行双击
            isDrawing = false; //结束绘画状态
            finished = true; //曲线绘制完成
            currentMousePosition = null;

            // 为每个线段计算贝塞尔控制句柄
            for (int i = 0; i < controlPoints.size() - 1; i++) {
                Point p0 = getPreviousPoint(controlPoints, i); //获取前一点
                Point p1 = controlPoints.get(i); //获取当前点
                Point p2 = controlPoints.get(i + 1); //获取下一点
                Point p3 = getNextPoint(controlPoints, i + 1); //获取下下一点

                List<Point> controls = calculateBezierControls(p0, p1, p2, p3); //根据四个点计算句柄
                p1.outHandle = controls.get(0);  // 该点的输出句柄
                p2.inHandle = controls.get(1);   // 下一点的输入句柄
            }
            redrawCanvas();
        }
    }

    /**
     * 处理左键单击 - 添加新的控制点
     */
    private void handleLeftClick(MouseEvent e) {
        if (!isDrawing && !finished) {  // 开始新的绘制
            controlPoints.clear();
            isDrawing = true; //表示正在绘制曲线
        }
        // 添加转换后的控制点坐标
        if(!finished) {
            controlPoints.add(screenToCanvas(new Point(e.getX(), e.getY()))); //添加控制点
            redrawCanvas();
        }
    }

    /**
     * 处理鼠标移动 - 更新预览位置
     */
    private void handleMouseMove(MouseEvent e) {
        if (isDrawing) {
            currentMousePosition = screenToCanvas(new Point(e.getX(), e.getY()));
            redrawCanvas();
        }
    }

    /**
     * 重绘整个画布
     */
    private void redrawCanvas() {
        clearCanvas();
        applyTransformations();  // 应用当前视图变换

        drawExistingCurve();     // 绘制已确定的曲线
        if (isDrawing) {
            drawPreviewCurve();  // 绘制预览中的临时曲线
        }
        drawControlPoints();     // 绘制所有控制点及其句柄
    }

    /**
     * 应用当前视图变换（平移和缩放）
     */
    private void applyTransformations() {
        gc.save();
        gc.translate(translate.x, translate.y); //进行图形移动
        gc.scale(scale, scale); //进行图形缩放
    }

    /**
     * 绘制已完成的曲线（贝塞尔曲线或临时Catmull-Rom曲线）
     */
    private void drawExistingCurve() {
        if (controlPoints.size() >= 2) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(1 / scale);  // 线宽根据缩放调整
            if (isDrawing) {
                drawCatmullRomCurve(controlPoints);  // 正在绘制，根据控制点绘制已确定的曲线
            } else {
                drawBezierCurveWithHandles();  // 绘制完成，根据句柄绘制曲线
            }
        }
    }

    /**
     * 曲线绘制完成，使用控制句柄绘制贝塞尔曲线
     */
    private void drawBezierCurveWithHandles() {
        gc.beginPath(); //开始绘制
        gc.moveTo(controlPoints.get(0).x, controlPoints.get(0).y); //从第一个位置开始

        // 遍历每对相邻控制点绘制曲线段
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            Point p1 = controlPoints.get(i);
            Point p2 = controlPoints.get(i + 1);

            if (p1.outHandle != null && p2.inHandle != null) {
                // 使用贝塞尔曲线连接
                gc.bezierCurveTo(
                        p1.outHandle.x, p1.outHandle.y,
                        p2.inHandle.x, p2.inHandle.y,
                        p2.x, p2.y
                );
            } else {
                // 没有句柄时使用直线连接
                gc.lineTo(p2.x, p2.y);
            }
        }
        gc.stroke(); //显示线条
    }

    /**
     * 绘制预览中的临时曲线（虚线Catmull-Rom）
     */
    private void drawPreviewCurve() {
        if (controlPoints.size() >= 1 && currentMousePosition != null) {
            List<Point> previewPoints = new ArrayList<>(controlPoints);
            previewPoints.add(currentMousePosition); //把当前鼠标的位置加入控制点

            gc.setStroke(Color.RED);
            gc.setLineWidth(1 / scale);
            gc.setLineDashes(5);  // 设置虚线样式
            drawCatmullRomCurve(previewPoints); //根据控制点绘制曲线
            gc.setLineDashes(null); // 重置虚线样式
        }
    }

    /**
     * 根据控制点绘制Catmull-Rom样条曲线，本质和使用控制句柄绘制一样
     */
    private void drawCatmullRomCurve(List<Point> points) {
        if (points.size() < 2) return;

        gc.beginPath();
        gc.moveTo(points.get(0).x, points.get(0).y);

        // 为每个线段计算贝塞尔控制点
        for (int i = 0; i < points.size() - 1; i++) {
            Point p0 = getPreviousPoint(points, i);
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            Point p3 = getNextPoint(points, i + 1);

            List<Point> controls = calculateBezierControls(p0, p1, p2, p3); //计算Catmull-Rom样条对应的贝塞尔控制点
            gc.bezierCurveTo( //绘制三次贝塞尔曲线
                    controls.get(0).x, controls.get(0).y,
                    controls.get(1).x, controls.get(1).y,
                    p2.x, p2.y
            );
        }
        gc.stroke();
    }

    /**
     * 计算Catmull-Rom样条对应的贝塞尔控制点
     * @param p0 前一个点
     * @param p1 当前点
     * @param p2 下一个点
     * @param p3 下下一个点
     * @return 包含两个控制点的列表（输出句柄和输入句柄）
     */
    private List<Point> calculateBezierControls(Point p0, Point p1, Point p2, Point p3) {
        double tension = 0.5; // 曲线张力参数,调节曲线的松紧程度
        return List.of(
                // p1输出控制点计算
                new Point( //方向向量由p2-p0得到，通过tension/3调整方向强度
                        p1.x + (p2.x - p0.x) * tension / 3,
                        p1.y + (p2.y - p0.y) * tension / 3
                ),
                // p2输入控制点计算
                new Point( //方向向量由p3-p1得到，通过tension/3调整方向强度
                        p2.x - (p3.x - p1.x) * tension / 3,
                        p2.y - (p3.y - p1.y) * tension / 3
                )
        );
    }

    /**
     * 绘制所有控制点及其关联的句柄和连接线
     */
    private void drawControlPoints() {
        for (int i=0; i<controlPoints.size(); i++) {
            Point p = controlPoints.get(i);
            // 绘制控制点本体（黑色）
            gc.setFill(Color.BLACK);
            gc.fillOval(
                    p.x - CONTROL_POINT_RADIUS / scale,
                    p.y - CONTROL_POINT_RADIUS / scale,
                    CONTROL_POINT_RADIUS * 2 / scale,
                    CONTROL_POINT_RADIUS * 2 / scale
            );

            // 绘制输出句柄（绿色）
            if (p.outHandle != null && p.showHandle && i!=controlPoints.size() - 1) { //最后一个点无输出句柄
                gc.setFill(Color.GREEN);
                gc.fillOval(
                        p.outHandle.x - CONTROL_POINT_RADIUS / scale,
                        p.outHandle.y - CONTROL_POINT_RADIUS / scale,
                        CONTROL_POINT_RADIUS * 2 / scale,
                        CONTROL_POINT_RADIUS * 2 / scale
                );
                // 绘制连接线
                gc.setStroke(Color.BLUE);
                gc.strokeLine(p.x, p.y, p.outHandle.x, p.outHandle.y);
            }

            // 绘制输入句柄（橙色）
            if (p.inHandle != null && p.showHandle && i!=0) { //第一个点无输入句柄
                gc.setFill(Color.ORANGE);
                gc.fillOval(
                        p.inHandle.x - CONTROL_POINT_RADIUS / scale,
                        p.inHandle.y - CONTROL_POINT_RADIUS / scale,
                        CONTROL_POINT_RADIUS * 2 / scale,
                        CONTROL_POINT_RADIUS * 2 / scale
                );
                // 绘制连接线
                gc.setStroke(Color.BLUE);
                gc.strokeLine(p.x, p.y, p.inHandle.x, p.inHandle.y);
            }
        }
    }

    /**
     * 清空画布并重置变换状态
     */
    private void clearCanvas() {
        gc.restore();  // 重置变换状态
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.save();     // 保存初始状态
    }

    /**
     * 屏幕坐标转换为画布逻辑坐标
     */
    private Point screenToCanvas(Point screenPoint) {
        return new Point(
                (screenPoint.x - translate.x) / scale,
                (screenPoint.y - translate.y) / scale
        );
    }

    /**
     * 画布逻辑坐标转换为屏幕坐标
     */
    private Point canvasToScreen(Point canvasPoint) {
        return new Point(
                canvasPoint.x * scale + translate.x,
                canvasPoint.y * scale + translate.y
        );
    }

    /**
     * 获取前一个点（处理边界情况）
     */
    private Point getPreviousPoint(List<Point> points, int index) {
        return index > 0 ? points.get(index - 1) : points.get(0);
    }

    /**
     * 获取下一个点（处理边界情况）
     */
    private Point getNextPoint(List<Point> points, int index) {
        return index < points.size() - 1 ? points.get(index + 1) : points.get(index);
    }

    /**
     * 计算两点之间的欧氏距离
     */
    private double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    /**
     * 自定义点类，包含坐标和贝塞尔句柄
     */
    private static class Point {
        double x, y;
        Point outHandle; // 输出方向的控制句柄
        Point inHandle;  // 输入方向的控制句柄
        boolean showHandle = false ; // 表示是否显示句柄

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}