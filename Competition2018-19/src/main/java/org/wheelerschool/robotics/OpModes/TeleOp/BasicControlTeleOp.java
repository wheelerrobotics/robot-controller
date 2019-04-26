package org.wheelerschool.robotics.OpModes.TeleOp;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;

import org.wheelerschool.robotics.Hardware;
import org.wheelerschool.robotics.robotlib.util.joystick.CumulativeControl;
import org.wheelerschool.robotics.robotlib.util.joystick.JoystickButtonUpdated;

import java.util.concurrent.Callable;

@TeleOp(name="Control")
public class BasicControlTeleOp extends OpMode {
    // CONST:
    final double INTAKE_POWER = 1;

    // Hardware:
    Hardware robot;

    // Joystick:
    CumulativeControl intakeAngle;
    JoystickButtonUpdated intakeCtl;
    JoystickButtonUpdated angleUp;
    JoystickButtonUpdated angleDown;

    // Control
    Integer armExtStopPos;

    public BasicControlTeleOp() {
        super();
        msStuckDetectInit = 8000;
        msStuckDetectInitLoop = 8000;
    }

    private void debugPrint() {
        telemetry.addData("Angle IDX", robot.armAngle.currentIdx);
        telemetry.addData("Lift", robot.lift.dcMotor.getCurrentPosition());
    }

    @Override
    public void init() {
        robot = new Hardware(hardwareMap);
        robot.lift.dcMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        intakeAngle = new CumulativeControl(0);

        intakeCtl = new JoystickButtonUpdated(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return gamepad1.a;
            }
        });
        angleUp = new JoystickButtonUpdated(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return gamepad2.dpad_up;
            }
        });
        angleDown = new JoystickButtonUpdated(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return gamepad2.dpad_down;
            }
        });
    }

    @Override
    public void loop() {
        if (angleUp.getValueIgnoreException().newStateTrue) {
            robot.armAngle.moveRel(-1, robot.INTAKE_UP_POWER);
        } else if (angleDown.getValueIgnoreException().newStateTrue) {
            robot.armAngle.moveTo(-1, robot.INTAKE_DOWN_POWER);
        } else {
            // (4^x - 1)/(4-1)
            final int SCALE_CONST = 4;
            float armAnglePwr = gamepad2.left_stick_y;
            armAnglePwr = Math.copySign(((float)Math.pow(SCALE_CONST, Math.abs(armAnglePwr)) - 1)/(SCALE_CONST-1), armAnglePwr);
            robot.armAngle.manualOverride(armAnglePwr);
            telemetry.addData("Arm Angle Manual", armAnglePwr);
        }


        int armExtPos = robot.armExt.dcMotor.getCurrentPosition();

        float armExtCtl = -gamepad2.right_stick_y;

        if (armExtCtl > 0) {
            armExtStopPos = null;
            robot.armExt.dcMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            armExtCtl *= 2. / 3.;
        } else if (armExtPos < 100) {
            armExtCtl = 0;
        } else if (armExtCtl == 0) {
            if (armExtStopPos == null) {
                armExtStopPos = robot.armExt.dcMotor.getCurrentPosition();
                if (armExtStopPos < 0 && false) {
                    armExtStopPos = 0;
                }
            }
            robot.armExt.dcMotor.setTargetPosition(armExtStopPos);
            robot.armExt.dcMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            armExtCtl = 1/8.f;
        } else if (armExtCtl < 0) {
            armExtStopPos = null;
            robot.armExt.dcMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            //robot.armExt.setTargetPosition(0);
            //robot.armExt.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            armExtCtl *= 1.;
        }
        telemetry.addData("arm", armExtCtl);
        robot.armExt.manualOverride(armExtCtl);

        float rotGain = 1;

        if (armExtPos > 500) {
            rotGain = 0.5f;
        }


        // Intake Angle:
        float angleCtl = gamepad2.right_trigger - gamepad2.left_trigger;

        intakeAngle.setCps(angleCtl);
        intakeAngle.positionTick(angleCtl != 0, getRuntime());
        robot.intakeAngle.setPosition(intakeAngle.getPosition());

        // Intake Drive:
        double intakeDrive = INTAKE_POWER;
        JoystickButtonUpdated.JoystickButtonData intakeIn = intakeCtl.getValueIgnoreException();
        if (gamepad1.y) {
            intakeCtl.lastFlipStateValue = false;
            intakeDrive = -intakeDrive;
        }
        else if (!intakeIn.flipStateValue) {
            intakeDrive = 0;
        }

        robot.intakeDrive.setPower(intakeDrive);

        // Lift Control:
        float liftGain = 1;
        if (gamepad1.dpad_up) {
            robot.lift.moveTo(1, 1.f);
        } else if (gamepad1.dpad_down) {
            robot.lift.moveTo(0, 1.f);
        } else if (gamepad1.right_bumper) {
            robot.lift.manualOverride(liftGain);
        } else if (gamepad1.left_bumper) {
            robot.lift.manualOverride(-liftGain);
        } else if (!robot.lift.motorActive()){
            robot.lift.stop();
        }
        // robot.lift.manualOverride(liftGain * (gamepad1.right_trigger - gamepad1.left_trigger));

        if (gamepad2.a) {
            robot.drop.setState(true);
        } else {
            robot.drop.setState(false);
        }

        // Team Marker
        robot.marker.setState(gamepad1.x);

        // DRIVE:
        telemetry.addData("x", gamepad1.left_stick_x);
        telemetry.addData("y", gamepad1.left_stick_y);
        telemetry.addData("rot", gamepad1.right_stick_x);
        telemetry.addData("arm angle", robot.armAngle.dcMotor.getCurrentPosition());
        robot.drive.updateMotors(gamepad1.left_stick_x, -gamepad1.left_stick_y, -gamepad1.right_stick_x * rotGain);
        //robot.drive.calcDrive(gamepad1.left_stick_x, gamepad1.left_stick_y, gamepad1.right_stick_x);

        debugPrint();
    }
}
